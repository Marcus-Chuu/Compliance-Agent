package com.nbcb.assistagent.security;

public class PromptInjectionException extends RuntimeException {

    public PromptInjectionException() {
        super("检测到请求中包含可能覆盖系统指令或获取内部提示词的内容，已拒绝处理。请只描述需要查询的制度或业务问题。");
    }
}
