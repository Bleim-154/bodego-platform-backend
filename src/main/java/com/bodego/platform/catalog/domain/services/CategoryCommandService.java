package com.bodego.platform.catalog.domain.services;

import com.bodego.platform.catalog.domain.model.aggregates.Category;
import com.bodego.platform.catalog.domain.model.commands.CreateCategoryCommand;
import com.bodego.platform.catalog.domain.model.commands.UpdateCategoryCommand;
import com.bodego.platform.catalog.domain.model.commands.DeleteCategoryCommand;

import java.util.Optional;

public interface CategoryCommandService {

    Long handle(CreateCategoryCommand command);

    Optional<Category> handle(UpdateCategoryCommand command);

    void handle(DeleteCategoryCommand command);
}
