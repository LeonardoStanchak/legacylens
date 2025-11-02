package br.com.legacylens.domain.ports;

import br.com.legacylens.domain.model.ProjectScan;

public interface ProjectScannerPort {
    ProjectScan scan(String pathOrJar);
}
