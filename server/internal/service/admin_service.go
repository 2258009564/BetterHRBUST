package service

import (
	"context"
	"errors"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/Glassous/BetterHRBUST/server/internal/model"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/jwtutil"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/password"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/randpass"
	"github.com/Glassous/BetterHRBUST/server/internal/repository"
)

// 管理端账号字段限制。
const (
	MinUsernameLength      = 3
	MaxUsernameLength      = 32
	MaxNicknameLength      = 32
	generatedPasswordBytes = 14
)

// 管理端业务错误。
var (
	ErrInvalidCredentials  = errors.New("invalid credentials")
	ErrAccountSuspended    = errors.New("account suspended")
	ErrUsernameTaken       = errors.New("username taken")
	ErrInvalidUsername     = errors.New("invalid username")
	ErrInvalidNickname     = errors.New("invalid nickname")
	ErrPasswordTooWeak     = errors.New("password too weak")
	ErrOldPasswordWrong    = errors.New("old password mismatch")
	ErrSamePassword        = errors.New("password unchanged")
	ErrCannotSuspendSelf   = errors.New("cannot suspend self")
	ErrSuperNotSuspendable = errors.New("super admin cannot be suspended")
	ErrNotFound            = errors.New("not found")
)

// AdminService 管理端账号业务逻辑。
type AdminService struct {
	repo      *repository.AdminRepository
	jwtSecret string
	jwtTTL    time.Duration
}

// NewAdminService 构造管理端服务。
func NewAdminService(repo *repository.AdminRepository, secret string, jwtTTL time.Duration) *AdminService {
	return &AdminService{repo: repo, jwtSecret: secret, jwtTTL: jwtTTL}
}

// LoginResult 登录成功后的返回内容。
type LoginResult struct {
	Token string       `json:"token"`
	Admin *model.Admin `json:"user"`
}

// Login 校验用户名口令并签发令牌。
func (s *AdminService) Login(ctx context.Context, username, plainPassword string) (*LoginResult, error) {
	username = strings.TrimSpace(username)
	admin, err := s.repo.FindByUsername(ctx, username)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			// 用户名不存在时也执行一次哈希运算，避免通过响应耗时枚举账号
			password.Verify("$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinva", plainPassword)
			return nil, ErrInvalidCredentials
		}
		return nil, err
	}
	if !password.Verify(admin.PasswordHash, plainPassword) {
		return nil, ErrInvalidCredentials
	}
	if admin.Status != model.StatusActive {
		return nil, ErrAccountSuspended
	}

	token, err := jwtutil.Issue(s.jwtSecret, s.jwtTTL, admin)
	if err != nil {
		return nil, err
	}
	return &LoginResult{Token: token, Admin: admin}, nil
}

// ChangePassword 管理员自行改密（首次登录强制改密也走此接口）。
func (s *AdminService) ChangePassword(ctx context.Context, adminID, oldPassword, newPassword string) (*model.Admin, error) {
	admin, err := s.repo.FindByID(ctx, adminID)
	if err != nil {
		return nil, ErrNotFound
	}
	if !password.Verify(admin.PasswordHash, oldPassword) {
		return nil, ErrOldPasswordWrong
	}
	if oldPassword == newPassword {
		return nil, ErrSamePassword
	}
	if !password.IsAcceptable(newPassword) {
		return nil, ErrPasswordTooWeak
	}

	hash, err := password.Hash(newPassword)
	if err != nil {
		return nil, err
	}
	if err := s.repo.UpdatePassword(ctx, adminID, hash, false); err != nil {
		return nil, err
	}
	return s.repo.FindByID(ctx, adminID)
}

// CreateAdminInput 超级管理员创建普通管理员时的输入。
type CreateAdminInput struct {
	Nickname string `json:"nickname"`
	Username string `json:"username"`
	Password string `json:"password"`
}

// CreateAdminResult 创建结果：初始口令仅在创建响应中返回一次。
type CreateAdminResult struct {
	Admin             *model.Admin `json:"user"`
	GeneratedPassword string       `json:"initialPassword"`
	Generated         bool         `json:"generated"`
}

// CreateAdmin 由超级管理员创建权限更低的管理员账号。
// 未提供口令时自动生成，且返回后不再可查。
func (s *AdminService) CreateAdmin(ctx context.Context, creator *model.Admin, input CreateAdminInput) (*CreateAdminResult, error) {
	nickname := strings.TrimSpace(input.Nickname)
	if nickname == "" || utf8.RuneCountInString(nickname) > MaxNicknameLength {
		return nil, ErrInvalidNickname
	}
	username, ok := normalizeUsername(input.Username)
	if !ok {
		return nil, ErrInvalidUsername
	}

	plain := strings.TrimSpace(input.Password)
	generated := false
	if plain == "" {
		plain = randpass.Generate(generatedPasswordBytes)
		generated = true
	} else if !password.IsAcceptable(plain) {
		return nil, ErrPasswordTooWeak
	}

	hash, err := password.Hash(plain)
	if err != nil {
		return nil, err
	}

	admin := &model.Admin{
		Username:           username,
		Nickname:           nickname,
		PasswordHash:       hash,
		Role:               model.RoleAdmin,
		Status:             model.StatusActive,
		MustChangePassword: true,
	}
	if creator != nil {
		admin.CreatedBy = creator.Username
	}
	if err := s.repo.Create(ctx, admin); err != nil {
		if errors.Is(err, repository.ErrUsernameTaken) {
			return nil, ErrUsernameTaken
		}
		return nil, err
	}
	return &CreateAdminResult{Admin: admin, GeneratedPassword: plain, Generated: generated}, nil
}

// List 返回全部管理端账号。
func (s *AdminService) List(ctx context.Context) ([]model.Admin, error) {
	return s.repo.List(ctx)
}

// SetStatus 暂停 / 恢复管理员资格；不允许操作自己，也不允许暂停超级管理员。
func (s *AdminService) SetStatus(ctx context.Context, actor *model.Admin, targetID, status string) (*model.Admin, error) {
	if status != model.StatusActive && status != model.StatusSuspended {
		return nil, ErrNotFound
	}
	target, err := s.repo.FindByID(ctx, targetID)
	if err != nil {
		return nil, ErrNotFound
	}
	if target.ID == actor.ID {
		return nil, ErrCannotSuspendSelf
	}
	if target.Role == model.RoleSuper && status != model.StatusActive {
		return nil, ErrSuperNotSuspendable
	}
	return s.repo.UpdateStatus(ctx, targetID, status)
}

// SeedSuperAdmin 创建初始超级管理员；账号已存在时返回 created=false。
func (s *AdminService) SeedSuperAdmin(ctx context.Context, username, nickname, plainPassword string) (*CreateAdminResult, bool, error) {
	username, ok := normalizeUsername(username)
	if !ok {
		return nil, false, ErrInvalidUsername
	}
	if nickname == "" {
		nickname = username
	}
	if utf8.RuneCountInString(nickname) > MaxNicknameLength {
		return nil, false, ErrInvalidNickname
	}

	if _, err := s.repo.FindByUsername(ctx, username); err == nil {
		return nil, false, nil
	} else if !errors.Is(err, repository.ErrNotFound) {
		return nil, false, err
	}

	generated := false
	plain := strings.TrimSpace(plainPassword)
	if plain == "" {
		plain = randpass.Generate(generatedPasswordBytes)
		generated = true
	} else if !password.IsAcceptable(plain) {
		return nil, false, ErrPasswordTooWeak
	}

	hash, err := password.Hash(plain)
	if err != nil {
		return nil, false, err
	}
	admin := &model.Admin{
		Username:           username,
		Nickname:           nickname,
		PasswordHash:       hash,
		Role:               model.RoleSuper,
		Status:             model.StatusActive,
		MustChangePassword: false,
	}
	if err := s.repo.Create(ctx, admin); err != nil {
		if errors.Is(err, repository.ErrUsernameTaken) {
			return nil, false, ErrUsernameTaken
		}
		return nil, false, err
	}
	return &CreateAdminResult{Admin: admin, GeneratedPassword: plain, Generated: generated}, true, nil
}

// normalizeUsername 规范化用户名：仅允许字母、数字、下划线、点与短横线。
func normalizeUsername(raw string) (string, bool) {
	username := strings.ToLower(strings.TrimSpace(raw))
	length := utf8.RuneCountInString(username)
	if length < MinUsernameLength || length > MaxUsernameLength {
		return "", false
	}
	for _, r := range username {
		switch {
		case r >= 'a' && r <= 'z':
		case r >= '0' && r <= '9':
		case r == '_' || r == '.' || r == '-':
		default:
			return "", false
		}
	}
	return username, true
}
