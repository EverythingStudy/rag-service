package cn.project.base.springaimcp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("mcp/")
public class McpClent {
    @GetMapping("test")
    public void mcp() {
        // ChatClient.builder()

    }
}
