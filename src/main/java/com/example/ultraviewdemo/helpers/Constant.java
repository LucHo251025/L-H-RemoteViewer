package com.example.ultraviewdemo.helpers;

public class Constant {
	public static final int ACTION_HOST = 0;
	public static final int ACTION_HOST_CONTROLLER  = ACTION_HOST +1;
	public static final int ACTION_VIEWER  = ACTION_HOST_CONTROLLER +1;
    public static final int ACTION_VIEWER_CONTROLLER  = ACTION_VIEWER +1;

    // Directory server signaling actions for P2P setup
    public static final int ACTION_HOST_REGISTER = ACTION_VIEWER_CONTROLLER + 1;
    public static final int ACTION_VIEWER_QUERY = ACTION_HOST_REGISTER + 1;
}
