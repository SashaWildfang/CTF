package com.bearnation.ctf.region;

import java.util.Map;

public class RegionSetupPayload {
    public String mapId;
    public Map<String, RegionBounds> regionBounds;
    public Map<String, Map<String, String>> regionFlags;
}
