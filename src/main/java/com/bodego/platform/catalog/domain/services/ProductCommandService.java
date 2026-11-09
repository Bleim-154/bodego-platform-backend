package com.bodego.platform.catalog.domain.services;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.commands.CreateProductCommand;
import com.bodego.platform.catalog.domain.model.commands.UpdateProductCommand;
import com.bodego.platform.catalog.domain.model.commands.ActivateProductCommand;
import com.bodego.platform.catalog.domain.model.commands.DeactivateProductCommand;

import java.util.Optional;

public interface ProductCommandService {

    Long handle(CreateProductCommand command);

    Optional<Product> handle(UpdateProductCommand command);

    Optional<Product> handle(ActivateProductCommand command);

    Optional<Product> handle(DeactivateProductCommand command);
}
