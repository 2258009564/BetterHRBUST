package service

import "testing"

func TestNormalizeUsername(t *testing.T) {
	cases := []struct {
		name   string
		input  string
		want   string
		wantOK bool
	}{
		{name: "普通用户名", input: "Admin_01", want: "admin_01", wantOK: true},
		{name: "含点与短横线", input: "review.er-1", want: "review.er-1", wantOK: true},
		{name: "过短", input: "ab", want: "", wantOK: false},
		{name: "含中文", input: "管理员一号", want: "", wantOK: false},
		{name: "含空格", input: "admin 01", want: "", wantOK: false},
		{name: "空字符串", input: "   ", want: "", wantOK: false},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, ok := normalizeUsername(tc.input)
			if ok != tc.wantOK || got != tc.want {
				t.Fatalf("normalizeUsername(%q) = (%q, %v)，期望 (%q, %v)", tc.input, got, ok, tc.want, tc.wantOK)
			}
		})
	}
}
