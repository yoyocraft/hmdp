package com.hmdp.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 5062298408582265225L;
    private Long id;
    private String nickName;
    private String icon;
}
