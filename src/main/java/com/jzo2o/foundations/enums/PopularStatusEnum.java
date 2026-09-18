package com.jzo2o.foundations.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PopularStatusEnum {
    ONHOT(1, "热门"),
    OFFHOT(0, "非热门");
    private int status;
    private String description;

    public boolean equals(Integer status) {
        return this.status == status;
    }

    public boolean equals(PopularStatusEnum popularStatusEnum) {
        return popularStatusEnum != null && popularStatusEnum.status == this.getStatus();
    }
}
