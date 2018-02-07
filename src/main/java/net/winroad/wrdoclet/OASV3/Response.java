package net.winroad.wrdoclet.OASV3;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter
@Setter
public class Response {
    private String description;
    private HashMap<String, MediaType> content;
}
