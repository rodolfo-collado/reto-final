package com.practice.retofinal.persistence;

import com.practice.retofinal.domain.CampusMap;
import java.io.IOException;
import java.nio.file.Path;

public interface MapRepository {
    CampusMap load(Path path) throws IOException;
    void save(Path path, CampusMap map) throws IOException;
}
