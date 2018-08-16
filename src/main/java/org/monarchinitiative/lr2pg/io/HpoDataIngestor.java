package org.monarchinitiative.lr2pg.io;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monarchinitiative.phenol.base.PhenolException;
import org.monarchinitiative.phenol.formats.hpo.HpoDisease;
import org.monarchinitiative.phenol.formats.hpo.HpoOntology;
import org.monarchinitiative.phenol.io.obo.hpo.HpOboParser;
import org.monarchinitiative.phenol.io.obo.hpo.HpoDiseaseAnnotationParser;
import org.monarchinitiative.phenol.ontology.data.TermId;

import java.io.File;
import java.util.Map;

/**
 * This is a convenience class that is designed to input both of the HP
 * files that we need to do the analysis, meaning hp.obo and phenotype.hpoa
 * @author <a href="mailto:peter.robinson@jax.org">Peter Robinson</a>
 */
public class HpoDataIngestor {
    private static final Logger logger = LogManager.getLogger();
    /** The directory (default: "data") where we have downloaded hp.obo and phenotype.hpoa. */
    private final String dataDirectoryPath;
    /** The file name of the HPO ontology file. */
    private static final String HP_OBO="hp.obo";
    /** The file name of the HPO annotation file. */
    private static final String HP_PHENOTYPE_ANNOTATION="phenotype.hpoa";
    /** An object representing the Human Phenotype Ontology */
    private HpoOntology ontology =null;
    /** Key: diseaseID, e.g., OMIM:600321; value: Corresponding HPO disease object. */
    private Map<TermId,HpoDisease> diseaseMap;

    public HpoDataIngestor(String dataDirectory) {
        this.dataDirectoryPath =dataDirectory;
        inputHpoOntologyAndAnnotations();
    }

    private void inputHpoOntologyAndAnnotations()  {
        String hpopath=String.format("%s%s%s",dataDirectoryPath, File.separator,HP_OBO);
        String annotationpath=String.format("%s%s%s",dataDirectoryPath,File.separator,HP_PHENOTYPE_ANNOTATION);
        HpOboParser parser = new HpOboParser(new File(hpopath));
        try {
            this.ontology = parser.parse();
        } catch (PhenolException ioe) {
            System.err.println("Could not parse hp.obo file: " + ioe.getMessage());
            throw new RuntimeException("Could not parse hp.obo file: " + ioe.getMessage());
        }
        HpoDiseaseAnnotationParser annotationParser=new HpoDiseaseAnnotationParser(annotationpath,ontology);
        try {
            this.diseaseMap = annotationParser.parse();
            if (! annotationParser.validParse()) {
                logger.error("Warning -- parse problems encountered with the annotation file at {}.", annotationpath);
                int n = annotationParser.getErrors().size();
                int i=0;
                for (String error: annotationParser.getErrors()) {
                    i++;
                    logger.error(i +"/"+n+") "+error);
                }
                logger.trace("Done showing errors");
            }
        } catch (PhenolException pe) {
            throw new RuntimeException("Could not parse annotation file: "+pe.getMessage());
        }
        logger.trace("Done parsing; diseasemap has {} entries", diseaseMap.size());
    }

    public HpoOntology getOntology() {
        return ontology;
    }

    public Map<TermId, HpoDisease> getDiseaseMap() {
        return diseaseMap;
    }
}
