package br.com.legacylens.domain.ports;

import br.com.legacylens.domain.model.ProjectScan;
import java.nio.file.Path;

public interface ReadmePort {
    Path write(ProjectScan scan, Path outDir);
}
