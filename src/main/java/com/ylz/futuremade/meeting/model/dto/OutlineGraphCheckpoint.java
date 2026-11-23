package com.ylz.futuremade.meeting.model.dto;

/**
 * 大纲和纪要 Graph 最近一次检查点，任务 ID 由任务行提供。
 * @param id Graph 检查点 ID
 * @param node 已执行节点
 * @param nextNode 恢复时进入的节点
 * @param round 大纲已完成的跨批归并轮次，零表示首批归并，纪要为空
 */
public record OutlineGraphCheckpoint(String id, String node, String nextNode, Integer round) {}
