package br.com.legacylens.application;

import br.com.legacylens.domain.model.ProjectScan;

public interface AnalyzeProjectService {
    ProjectScan execute(String pathOrJar);
}
