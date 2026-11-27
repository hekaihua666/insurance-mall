package cn.study.insurance.controller;

import cn.study.insurance.security.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 会话与 Agent 暂由 Python 处理；逐块转发响应以保留原有 SSE 流。 */
@RestController
public class AgentProxyController {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${agent.base-url}")
    private String agentBaseUrl;

    @RequestMapping({"/api/v1/chat", "/api/v1/chat-threads", "/api/v1/chat-threads/**"})
    public ResponseEntity<StreamingResponseBody> forward(HttpServletRequest request,
                                                         @RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId) throws Exception {
        String query = request.getQueryString(); // "page=1"   查询参数
        URI uri = URI.create(agentBaseUrl + request.getRequestURI() + (query == null ? "" : "?" + query));
        byte[] body = request.getInputStream().readAllBytes();// body 的输入流（读请求体）

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .method(request.getMethod(), HttpRequest.BodyPublishers.ofByteArray(body))
                // 给即将发给 Python 的请求增加一个请求头，头名叫 x-user-id，值是当前登录用户的 ID。
                .header("x-user-id", userId.toString());


        for (String name : new String[]{"Content-Type", "Accept"}) {
            String value = request.getHeader(name);
            if (value != null) {
                builder.header(name, value);
            }
        }
        //给python发请求
        HttpResponse<java.io.InputStream> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofInputStream());//流式返回

        //返回给前端的响应头
        HttpHeaders headers = new HttpHeaders();
        // 将 Python 的响应头 Content-Type（响应格式） 复制到前端的响应头
        response.headers().firstValue("content-type").ifPresent(value -> headers.set("Content-Type", value));
        // 不缓存
        headers.set("Cache-Control", "no-cache");

        // 每读一块就 flush，保证前端能看到逐字输出的流式效果。
        StreamingResponseBody stream = output -> {
            try (var input = response.body()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    output.flush();
                }
            }
        };

        return new ResponseEntity<>(stream, headers, HttpStatusCode.valueOf(response.statusCode()));
    }
}
