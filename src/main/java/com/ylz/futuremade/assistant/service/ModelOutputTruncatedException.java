package com.ylz.futuremade.assistant.service;
/** 模型输出触及长度限制，结果不能作为完整内容使用。 */
public class ModelOutputTruncatedException extends IllegalStateException {
    public ModelOutputTruncatedException() { super("模型输出因长度限制被截断"); }
}
