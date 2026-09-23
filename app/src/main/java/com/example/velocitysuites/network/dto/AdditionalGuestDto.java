package com.example.velocitysuites.network.dto;

public class AdditionalGuestDto {
    public String name;
    public int age;
    public String gender;
    public String relationship;

    public AdditionalGuestDto(String name, int age, String gender, String relationship) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.relationship = relationship;
    }
}
