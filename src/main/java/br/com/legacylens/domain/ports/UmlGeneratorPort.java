package br.com.legacylens.domain.ports;

import br.com.legacylens.domain.model.UmlDiagram;
import java.nio.file.Path;

public interface UmlGeneratorPort {
    UmlDiagram generateFromPathOrJar(String pathOrJar, Path outDir);
}
