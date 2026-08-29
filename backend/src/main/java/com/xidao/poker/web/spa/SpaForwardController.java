package com.xidao.poker.web.spa;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 只回退当前 React Router 拥有的页面路径。API、WebSocket 和静态资源不经过这里，
 * 因此不存在把 404 JSON 或缺失资源错误包装成 index.html 的情况。
 */
@Controller
public class SpaForwardController {
    @GetMapping({"/rooms/{roomId:[A-Za-z0-9_-]+}", "/rooms/{roomId:[A-Za-z0-9_-]+}/"})
    public String room(@PathVariable String roomId) {
        return "forward:/index.html";
    }
}
