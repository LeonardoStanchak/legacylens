package br.com.legacylens.domain.ports;

import br.com.legacylens.domain.model.UmlDiagram;

import java.nio.file.Path;

public interface SequenceDiagramPort {
    UmlDiagram generateFromPathOrJar(String source, Path outDir);
}
