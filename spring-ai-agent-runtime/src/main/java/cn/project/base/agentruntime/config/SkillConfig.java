package cn.project.base.agentruntime.config;

import cn.project.base.agentruntime.skill.builtin.KnowledgeSearchSkill;
import cn.project.base.agentruntime.skill.builtin.TimeSkill;
import cn.project.base.agentruntime.skill.builtin.WeatherSkill;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册内置技能 —— 每个技能是一个带有 @Tool 方法的 Spring Bean，
 * 通过 MethodToolCallbackProvider 转换成 ToolCallbackProvider，
 * 最终注入到 ChatClient 中供 AI 模型调用。
 */
@Configuration
public class SkillConfig {

    @Bean
    public ToolCallbackProvider weatherSkillProvider(WeatherSkill weatherSkill) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherSkill)
                .build();
    }

    @Bean
    public ToolCallbackProvider timeSkillProvider(TimeSkill timeSkill) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(timeSkill)
                .build();
    }

    @Bean
    public ToolCallbackProvider knowledgeSearchProvider(KnowledgeSearchSkill knowledgeSearchSkill) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(knowledgeSearchSkill)
                .build();
    }
}
