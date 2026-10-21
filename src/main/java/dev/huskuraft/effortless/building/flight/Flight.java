package dev.huskuraft.effortless.building.flight;

import dev.huskuraft.effortless.building.Option;

public enum Flight implements Option {
    DISABLED("flight_disabled"),
    ENABLED("flight_enabled");

    private final String name;

    Flight(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCategory() {
        return "flight";
    }

}
