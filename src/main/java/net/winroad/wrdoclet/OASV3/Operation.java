package net.winroad.wrdoclet.OASV3;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

@Getter
@Setter
public class Operation {
    private String operationId;
    private List<String> tags;
    private RequestBody requestBody;
    private HashMap<String, Response> responses;
}
