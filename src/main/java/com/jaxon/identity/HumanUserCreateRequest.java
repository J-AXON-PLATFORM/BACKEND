package com.jaxon.identity;

import java.util.List;

record HumanUserCreateRequest(String username, String password, List<String> roles) {}
