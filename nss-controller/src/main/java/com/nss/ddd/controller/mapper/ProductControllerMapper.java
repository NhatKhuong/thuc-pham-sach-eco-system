package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.controller.dto.CreateProductRequest;
import com.nss.ddd.controller.dto.UpdateProductRequest;

/**
 * Converter ở ranh giới HTTP: {@code *Request} của controller sang {@code *Command} của application
 * (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * Không {@code BeanUtils.copyProperties} — hai kiểu trùng tên trường hôm nay không có nghĩa là
 * chúng sẽ trùng mãi, và bản chép ngầm sẽ im lặng khi chúng tách ra.
 */
public final class ProductControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private ProductControllerMapper() {
    }

    /**
     * @param request body của {@code POST /api/products}
     * @return lệnh tạo, hoặc {@code null} khi {@code request} rỗng
     */
    public static CreateProductCommand toCommand(CreateProductRequest request) {
        if (request == null) {
            return null;
        }
        return new CreateProductCommand()
                .setSlug(request.getSlug())
                .setName(request.getName())
                .setShortDescription(request.getShortDescription())
                .setDescription(request.getDescription())
                .setPrice(request.getPrice())
                .setSalePrice(request.getSalePrice())
                .setUnit(request.getUnit())
                .setOrigin(request.getOrigin())
                .setStock(request.getStock())
                .setIsFeatured(request.getIsFeatured())
                .setIsBestSeller(request.getIsBestSeller())
                .setCategoryId(request.getCategoryId())
                .setBrandId(request.getBrandId())
                .setImages(request.getImages());
    }

    /**
     * @param request body của {@code PUT /api/products/{id}}
     * @return lệnh cập nhật, hoặc {@code null} khi {@code request} rỗng
     */
    public static UpdateProductCommand toCommand(UpdateProductRequest request) {
        if (request == null) {
            return null;
        }
        return new UpdateProductCommand()
                .setSlug(request.getSlug())
                .setName(request.getName())
                .setShortDescription(request.getShortDescription())
                .setDescription(request.getDescription())
                .setPrice(request.getPrice())
                .setSalePrice(request.getSalePrice())
                .setUnit(request.getUnit())
                .setOrigin(request.getOrigin())
                .setStock(request.getStock())
                .setIsFeatured(request.getIsFeatured())
                .setIsBestSeller(request.getIsBestSeller())
                .setCategoryId(request.getCategoryId())
                .setBrandId(request.getBrandId())
                .setImages(request.getImages());
    }
}
