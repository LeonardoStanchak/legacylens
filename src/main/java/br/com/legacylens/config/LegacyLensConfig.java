package br.com.legacylens.config;

import lombok.Data;
import java.util.List;
import java.util.Objects;

/**
 * 🧠 Modelo central do arquivo legacylens.yml
 *  - Tolerante a variações de chave (legados antigos)
 *  - Com setters retrocompatíveis e saneamento automático
 */
@Data
public class LegacyLensConfig {

    private General general = new General();
    private Uml uml = new Uml();
    private Sequence sequence = new Sequence();
    private Reports reports = new Reports();
    private Execution execution = new Execution();
    private Theme theme = new Theme();
    private Validation validation = new Validation();

    /** 🔧 Normaliza valores nulos após o carregamento */
    public void sanitize() {
        if (general == null) general = new General();
        if (uml == null) uml = new Uml();
        if (sequence == null) sequence = new Sequence();
        if (reports == null) reports = new Reports();
        if (execution == null) execution = new Execution();
        if (theme == null) theme = new Theme();
        if (validation == null) validation = new Validation();
    }

    // ============================================================
    // 🔹 GENERAL
    // ============================================================
    @Data
    public static class General {
        private String projectName = "LegacyLens";
        private String version = "1.0.0";
        private String logLevel = "INFO";
        private String outputDir = "output";
        private boolean deleteTemp = true;
        private boolean timestampedOutput = true;
        private String language = "pt-BR";
        private String environment = "local";
    }

    // ============================================================
    // 🔹 UML
    // ============================================================
    @Data
    public static class Uml {
        private boolean enabled = true;
        private boolean includeInterfaces = true;
        private boolean includeAbstract = true;
        private boolean includeRelationships = true;
        private int maxClasses = 500;
        private boolean truncateLongPackages = true;
        private String theme = "default";
        private boolean fallbackEnabled = true;
        private String outputName = "diagram.puml";

        // 👇 Retrocompatibilidade total
        public void setIncludeAbstractClasses(boolean value) {
            this.includeAbstract = value;
        }
        public void setIncludeAbstractClass(boolean value) {
            this.includeAbstract = value;
        }
        public void setIncludeRelations(boolean value) {
            this.includeRelationships = value;
        }
        public void setIncludeRelation(boolean value) {
            this.includeRelationships = value;
        }
        public void setMaxClassCount(int value) {
            this.maxClasses = value;
        }
        public void setLimitClasses(int value) {
            this.maxClasses = value;
        }
        public void setTrimPackages(boolean value) {
            this.truncateLongPackages = value;
        }
        public void setShortPackages(boolean value) {
            this.truncateLongPackages = value;
        }
    }

    // ============================================================
    // 🔹 SEQUENCE
    // ============================================================
    @Data
    public static class Sequence {
        private boolean enabled = true;
        private String mode = "engineering";
        private boolean includeActor = true;
        private boolean includeGroups = true;
        private boolean translateMethods = true;
        private String colorTheme = "default";
        private int maxDepth = 3;
        private String outputPattern = "sequence_{controller}.puml";
        private boolean autoDetectArchitecture = true;
        private boolean showInternalProcessing = true;
        private boolean showNotes = true;
        private int truncateFields = 10;
        private String compactMode = "auto";
        private boolean detectRepetitions = true;
        private boolean cacheControllers = true;

        // 👇 Setters retrocompatíveis
        public void setTranslateMethodNames(boolean value) {
            this.translateMethods = value;
        }
        public void setAutoTranslate(boolean value) {
            this.translateMethods = value;
        }
        public void setDiagramMode(String value) {
            this.mode = value;
        }
        public void setType(String value) {
            this.mode = value;
        }
    }

    // ============================================================
    // 🔹 REPORTS
    // ============================================================
    @Data
    public static class Reports {
        private Excel excel = new Excel();
        private Readme readme = new Readme();

        @Data
        public static class Excel {
            private boolean enabled = true;
            private String sheetName = "Comparativo Legado x Novo";
            private boolean includeTimestamp = true;
            private String style = "corporate";
            private boolean autosizeColumns = true;
            private int maxRows = 5000;

            // 👇 Retrocompatibilidade
            public void setSheet(String name) { this.sheetName = name; }
            public void setTabName(String name) { this.sheetName = name; }
            public void setMaxRowsCount(int value) { this.maxRows = value; }
            public void setRowsLimit(int value) { this.maxRows = value; }
        }

        @Data
        public static class Readme {
            private boolean enabled = true;
            private boolean includeDiagrams = true;
            private boolean includeSummaryTable = true;
            private boolean includeFileStats = true;
            private String format = "markdown";
            private String language = "pt-BR";
        }
    }

    // ============================================================
    // 🔹 EXECUTION
    // ============================================================
    @Data
    public static class Execution {
        private int compileTimeoutMinutes = 5;
        private boolean useMavenWrapper = true;
        private boolean useGradleWrapper = true;
        private boolean fallbackToJavaCompiler = true;
        private int maxScanDepth = 5;
        private boolean skipTests = true;
        private boolean cleanBeforeCompile = true;
        private boolean detectMultiModule = true;

        // 👇 Retrocompatibilidade
        public void setCompileTimeout(int value) { this.compileTimeoutMinutes = value; }
        public void setTimeoutMinutes(int value) { this.compileTimeoutMinutes = value; }
    }

    // ============================================================
    // 🔹 THEME
    // ============================================================
    @Data
    public static class Theme {
        private String actorColor = "#CCCCCC";
        private String controllerColor = "#A9D0F5";
        private String serviceColor = "#A9F5BC";
        private String repositoryColor = "#F5A9A9";
        private String noteColor = "#DDDDDD";
        private String borderColor = "#B0B0B0";
        private String font = "Consolas";
        private int fontSize = 12;
    }

    // ============================================================
    // 🔹 VALIDATION
    // ============================================================
    @Data
    public static class Validation {
        private boolean enableSwaggerDetection = true;
        private boolean enableHeuristics = true;
        private boolean validateDtoFields = true;
        private boolean detectArchitecture = true;
        private int maxMethodScan = 1000;
        private List<String> ignorePatterns =
                List.of("equals", "hashCode", "toString", "builder", "logger", "trace");

        // 👇 Garantia contra nulos
        public List<String> getIgnorePatterns() {
            return Objects.requireNonNullElse(ignorePatterns,
                    List.of("equals", "hashCode", "toString", "builder", "logger", "trace"));
        }

        // 👇 Retrocompatibilidade
        public void setIgnoredPatterns(List<String> patterns) { this.ignorePatterns = patterns; }
        public void setIgnoreList(List<String> patterns) { this.ignorePatterns = patterns; }
    }
}
