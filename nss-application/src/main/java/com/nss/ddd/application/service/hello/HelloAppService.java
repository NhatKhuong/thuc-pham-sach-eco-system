package com.nss.ddd.application.service.hello;

import com.nss.ddd.application.model.response.HelloResponse;

/**
 * Use case `hello` — lắp kết quả lấy từ domain thành response cho controller.
 */
public interface HelloAppService {

    /**
     * @return response chứa lời chào và tầng đã sinh ra nó
     */
    HelloResponse getHello();
}
