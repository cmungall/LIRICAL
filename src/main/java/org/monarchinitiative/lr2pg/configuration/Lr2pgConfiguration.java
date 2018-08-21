package org.monarchinitiative.lr2pg.configuration;

import com.google.common.collect.Multimap;
import org.monarchinitiative.lr2pg.hpo.HpoPhenoGenoCaseSimulator;
import org.monarchinitiative.lr2pg.hpo.PhenotypeOnlyHpoCaseSimulator;
import org.monarchinitiative.phenol.base.PhenolException;
import org.monarchinitiative.phenol.formats.hpo.HpoDisease;
import org.monarchinitiative.phenol.formats.hpo.HpoOntology;
import org.monarchinitiative.phenol.io.assoc.HpoAssociationParser;
import org.monarchinitiative.phenol.io.obo.hpo.HpOboParser;
import org.monarchinitiative.phenol.io.obo.hpo.HpoDiseaseAnnotationParser;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

import java.io.File;
import java.util.List;
import java.util.Map;

@Configuration
@PropertySource("classpath:application.properties")
public class Lr2pgConfiguration {

    public static final Logger logger = LoggerFactory.getLogger(Lr2pgConfiguration.class);

    @Bean(name="hpoOboFile")
    public File hpoOboFile() {
        File namedFile = new File("data/hp.obo");
        return namedFile;
    }

    @Bean(name="phenotype.hpoa")
    public File annotationFile() {
        File phenotypeHpoa = new File("data/phenotype.hpoa");
        return phenotypeHpoa;
    }



    @Autowired
    private final Environment env;

    public Lr2pgConfiguration(Environment env) {
        this.env = env;
    }


    @Bean
    @Primary
    public HpoOntology hpoOntology(){
        HpOboParser parser = new HpOboParser(hpoOboFile());
        HpoOntology ontology;
        try {
            ontology = parser.parse();
            return ontology;
        } catch (PhenolException ioe) {
            System.err.println("Could not parse hp.obo file: " + ioe.getMessage());
            throw new RuntimeException("Could not parse hp.obo file: " + ioe.getMessage());
        }
    }


    @Bean
    @Primary
    public Map<TermId,HpoDisease> diseaseMap(HpoOntology ontology) {
        HpoDiseaseAnnotationParser annotationParser=new HpoDiseaseAnnotationParser(annotationFile(),ontology);
        try {
            Map<TermId,HpoDisease> diseaseMap = annotationParser.parse();
            logger.info("disease map size="+diseaseMap.size());
            if (! annotationParser.validParse()) {
                logger.debug("Parse problems encountered with the annotation file at {}.",
                        annotationFile().getAbsolutePath());
                int n = annotationParser.getErrors().size();
                int i=0;
                for (String error: annotationParser.getErrors()) {
                    i++;
                    logger.debug(i +"/"+n+") "+error);
                }
                logger.debug("Done showing errors");
            }
            return diseaseMap;
        } catch (PhenolException pe) {
            throw new RuntimeException("Could not parse annotation file: "+pe.getMessage());
        }
    }

    @Value("${cases_to_simulate}")
    private String cases_to_simulate;
    @Value("${terms_per_case}")
    private String terms_per_case;
    @Value("${noise_terms}")
    private String noise_terms;
    @Value("${imprecise}")
    private String imprecise;





    @Bean
    @Primary
    PhenotypeOnlyHpoCaseSimulator phenotypeOnlyHpoCaseSimulator(HpoOntology ontology, Map<TermId,HpoDisease> diseaseMap) {
        int n_cases_to_simulate = Integer.parseInt(cases_to_simulate);
        int n_terms_per_case = Integer.parseInt(terms_per_case);
        int n_noise_terms = Integer.parseInt(noise_terms);
        boolean imprecise_phenotype=false;
        if (imprecise!=null && imprecise.equals("true"))
            imprecise_phenotype=true;
        return new PhenotypeOnlyHpoCaseSimulator(ontology,
                diseaseMap,
                n_cases_to_simulate,
                n_terms_per_case,
                n_noise_terms,
                imprecise_phenotype);
    }



    @Value("${data.path}")
    private String datapath;

    @Bean
    HpoAssociationParser hpoAssociationParser(HpoOntology ontology) {
        String geneInfoPath=String.format("%s%s%s",datapath,File.separator,"Homo_sapiens_gene_info.gz");
        File geneInfoFile = new File(geneInfoPath);
        if (!geneInfoFile.exists()) {
            System.err.println("Could not find gene info file at " + geneInfoPath +". Run download command");
            System.exit(1);
        }
        String mim2genemedgen=String.format("%s%s%s",datapath,File.separator,"mim2gene_medgen");
        File mim2genemedgenFile=new File(mim2genemedgen);
        if (!mim2genemedgenFile.exists()) {
            System.err.println("Could not find medgen file at " + mim2genemedgen +". Run download command");
            System.exit(1);
        }
        File orphafilePlaceholder=null;
        HpoAssociationParser assocParser = new HpoAssociationParser(geneInfoFile,
                mim2genemedgenFile,
                orphafilePlaceholder,
                ontology);
        return assocParser;
    }




    /**  key: a gene CURIE such as NCBIGene:123; value: a collection of disease CURIEs such as OMIM:600123. */
    @Bean
    Multimap<TermId,TermId> gene2diseaseMultimap(HpoAssociationParser parser) {
         return parser.getGeneToDiseaseIdMap();
    }
    /* key: disease CURIEs such as OMIM:600123; value: a collection of gene CURIEs such as NCBIGene:123.  */

    @Bean
    Multimap<TermId,TermId> disease2geneMultimap(HpoAssociationParser parser) {
        return parser.getDiseaseToGeneIdMap();
    }
    /** key: a gene id, e.g., NCBIGene:2020; value: the corresponding symbol. */
    @Bean
    Map<TermId,String> geneId2symbolMap(HpoAssociationParser parser) {
        return parser.getGeneIdToSymbolMap();
    }

    @Value("${varcount}")
    private String varcount;
    @Value("${varpath}")
    private String varpath;
    @Value("${entrezgeneid}")
    private String entrezgeneid;


    @Bean
    HpoPhenoGenoCaseSimulator hpoPhenoGenoCaseSimulator(HpoOntology ontology,
                                                        Map<TermId,HpoDisease> diseaseMap) {

    /*
        HpoPhenoGenoCaseSimulator(HpoOntology ontology,
                Map<TermId,HpoDisease> diseaseMap,
                Multimap<TermId,TermId> disease2geneMultimap,
                String entrezGeneNumber,
        int varcount,
        double varpath,
        List<TermId> hpoTerms,
        Map<TermId,Double> gene2backgroundFrequency)
        */
    return null;
    }



}
