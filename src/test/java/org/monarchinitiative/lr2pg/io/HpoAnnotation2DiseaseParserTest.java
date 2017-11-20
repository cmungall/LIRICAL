package org.monarchinitiative.lr2pg.io;

import com.github.phenomics.ontolib.formats.hpo.HpoTerm;
import com.github.phenomics.ontolib.formats.hpo.HpoTermRelation;
import com.github.phenomics.ontolib.ontology.data.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.monarchinitiative.lr2pg.hpo.HpoDiseaseWithMetadata;
import org.monarchinitiative.lr2pg.hpo.ImmutableTermIdWithMetadata;
import org.monarchinitiative.lr2pg.hpo.TermIdWithMetadata;

import javax.smartcardio.TerminalFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class HpoAnnotation2DiseaseParserTest {
    /** The subontology of the HPO with all the phenotypic abnormality terms. */
    private static Ontology<HpoTerm, HpoTermRelation> phenotypeSubOntology =null;
    /** The subontology of the HPO with all the inheritance terms. */
    private static Ontology<HpoTerm, HpoTermRelation> inheritanceSubontology=null;

    private static HpoAnnotation2DiseaseParser annotationParser=null;

    private static Map<String,HpoDiseaseWithMetadata> diseaseMap;

    private static TermPrefix HP_PREFIX=new ImmutableTermPrefix("HP");

    @BeforeClass
    public static void setup() throws IOException {
        ClassLoader classLoader = HPOOntologyParserTest.class.getClassLoader();
        String hpoPath = classLoader.getResource("hp.obo").getFile();
        HpoOntologyParser parser = new HpoOntologyParser(hpoPath);
        String annotationPath = classLoader.getResource("small_phenoannot.tab").getFile();
        parser.parseOntology();
        phenotypeSubOntology = parser.getPhenotypeSubontology();
        inheritanceSubontology = parser.getInheritanceSubontology();
        HP_PREFIX = new ImmutableTermPrefix("HP");
        annotationParser = new HpoAnnotation2DiseaseParser(annotationPath,phenotypeSubOntology,inheritanceSubontology);
        diseaseMap=annotationParser.getDiseaseMap();
    }


    @Test
    public void notNullTest() {
        assertNotNull(annotationParser!=null);
    }

    /**
     * small_phenoannot.tab contains annotations for a total of 196 diseases:
     * <pre>
     * $ cut -f 2 small_phenoannot.tab | sort | uniq | wc -l
     * 196
     * </pre>
     */
    @Test
    public void testGetRightNumberOfDiseases() {
        int expected = 196;
        assertEquals(expected,diseaseMap.size());
    }


    @Test public void testAcrocephalopolysyndactylyAnnotations() {
        HpoDiseaseWithMetadata disease = diseaseMap.get("101120");
        assertNotNull(disease);
    }

    /** Acrocephalopolysyndactlyly is annotated to     HP:0000006 , Autosomal dominant inheritance */
    @Test public void testAcrocephalopolysyndactylyInheritance() {
        HpoDiseaseWithMetadata disease = diseaseMap.get("101120");
        List<TermId> inherTerms = disease.getModesOfInheritance();
        assertEquals(1,inherTerms.size());
        TermId autosomaldominant = new ImmutableTermId(HP_PREFIX,"0000006");
        assertEquals(autosomaldominant,inherTerms.get(0));
    }

    /**
     * crocephalopolysyndactlyly is annotated to 17 phenpotype terms (we exclude autosomal dominant, HP:0000006).
     *  grep ACROCEPHALOPOLYSYNDACTYLY small_phenoannot.tab | grep -v 0000006 | wc -l
     * 17
     */
    @Test public void testAcrocephalopolysyndactylyPhenoAnnotations() {
        int expected = 17;
        HpoDiseaseWithMetadata disease = diseaseMap.get("101120");
        assertEquals(expected,disease.getPhenotypicAbnormalities().size());
        TermId oxycephaly = new ImmutableTermId(HP_PREFIX,"0000263");
        TermIdWithMetadata oxycephaly_withmetadata = new ImmutableTermIdWithMetadata( oxycephaly,null,null);
        assertTrue(disease.getPhenotypicAbnormalities().contains(oxycephaly_withmetadata));
        TermId abnEKG = new ImmutableTermId(HP_PREFIX,"0003115");
        TermIdWithMetadata abnEKGmd = new ImmutableTermIdWithMetadata(abnEKG,null,null);
        // this term does not annotate Acrocephalopolysyndactyly
        assertFalse(disease.getPhenotypicAbnormalities().contains(abnEKGmd));
        expected=0;
        assertEquals(expected,disease.getNegativeAnnotations().size());
    }



    /*
    * 111400	#111400 BLOOD GROUP, P1PK SYSTEMP(1) PHENOTYPE..has two annotations
     * Blood group antigen abnormality: HP:0010970
     * Also autosomal dominant HP:0000006
     */
    @Test
    public void testP1PKBloodGroupAnnotations() {
        int expected = 1;
        HpoDiseaseWithMetadata disease = diseaseMap.get("111400");
        assertEquals(expected,disease.getPhenotypicAbnormalities().size());
        TermId AntigenAbn = new ImmutableTermId(HP_PREFIX,"0010970");
        TermIdWithMetadata antigenAbnMD = new ImmutableTermIdWithMetadata(AntigenAbn,null,null);
        assertTrue(disease.getPhenotypicAbnormalities().contains(antigenAbnMD));
        List<TermId> inherTerms = disease.getModesOfInheritance();
        assertEquals(1,inherTerms.size());
        TermId autosomaldominant = new ImmutableTermId(HP_PREFIX,"0000006");
        assertEquals(autosomaldominant,inherTerms.get(0));

    }




}