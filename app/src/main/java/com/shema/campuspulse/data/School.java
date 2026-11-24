package com.shema.campuspulse.data;

import java.util.List;

public class School {
    public final String code;
    public final String name;
    public final List<String> majors;

    public School(String code, String name, List<String> majors) {
        this.code = code;
        this.name = name;
        this.majors = majors;
    }
}