package com.bodego.platform.iam.domain.model.valueobjects;

import com.bodego.platform.iam.domain.model.aggregates.User;
import java.util.List;

public record UserDirectoryPage(List<User> users, int page, int size, boolean hasNext) {
    public UserDirectoryPage { users=List.copyOf(users); }
}
