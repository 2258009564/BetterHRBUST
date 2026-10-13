package config

import "testing"

func TestParseReplyTemplatesDefaults(t *testing.T) {
	if got := parseReplyTemplates("   "); len(got) != len(DefaultReplyTemplates) {
		t.Fatalf("留空时应回退内置文案，实际 %d 条", len(got))
	}
	if got := parseReplyTemplates("::"); len(got) != len(DefaultReplyTemplates) {
		t.Fatalf("全部非法时应回退内置文案，实际 %d 条", len(got))
	}
}

func TestParseReplyTemplatesCustom(t *testing.T) {
	got := parseReplyTemplates("已收到::感谢反馈|| 需要补充信息 :: 请补充页面与步骤 ")
	if len(got) != 2 {
		t.Fatalf("期望解析出 2 条文案，实际 %d 条", len(got))
	}
	if got[0].Label != "已收到" || got[0].Content != "感谢反馈" {
		t.Fatalf("第一条解析结果不正确: %+v", got[0])
	}
	if got[1].Label != "需要补充信息" || got[1].Content != "请补充页面与步骤" {
		t.Fatalf("第二条应去除首尾空白: %+v", got[1])
	}
}

// 省略 :: 时按钮文案自动取正文前 10 个字，避免出现空白按钮。
func TestParseReplyTemplatesDerivesLabel(t *testing.T) {
	got := parseReplyTemplates("这是一段很长很长很长的回复正文内容")
	if len(got) != 1 {
		t.Fatalf("期望解析出 1 条文案，实际 %d 条", len(got))
	}
	if got[0].Label != "这是一段很长很长很长…" {
		t.Fatalf("按钮文案应截取前 10 个字，实际: %q", got[0].Label)
	}
	if got[0].Content != "这是一段很长很长很长的回复正文内容" {
		t.Fatalf("正文不应被截断: %q", got[0].Content)
	}
}
