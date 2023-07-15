package com.hmdp.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    public UserDTO(){
    }
    public UserDTO(Long aId,String aNickName,String aIcon){
        id=aId;
        nickName=aNickName;
        icon=aIcon;
    }
}
