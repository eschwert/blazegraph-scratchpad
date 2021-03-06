# Entailment, inference, and truth in Blazegraph

There are two general approaches to handling entailments: eagerly computing them as data is inserted, and waiting to derive them until query time.

Eagerly computing inferences has the advantages of front-loading the computation, so that queries can later be planned and run efficiently.  Its drawbacks include the up-front expense of performing this computation, the increased space of storing the inferred triples, and the burden of maintaining truth (i.e. recomputing the inferences) as data changes over time.

Deriving inferences on-demand at query time advoids these drawbacks, but increases query planning complexity and query execution time.

Blazegraph combines the approaches.

## Usage

The code in this project is for fiddling.  Run it with Maven:

```
$ mvn test

...

Results :

Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 7.157 s
[INFO] Finished at: 2015-02-19T14:55:01-08:00
[INFO] Final Memory: 11M/217M
[INFO] ------------------------------------------------------------------------
```

## Background

Consider the following triples:

```
(1) ex:book1 rdf:type ex:Publication .
(2) ex:book2 rdf:type ex:Article .
(3) ex:Article rdfs:subClassOf ex:Publication .
(4) ex:publishes rdfs:range ex:Publication .
(5) ex:MITPress ex:publishes ex:book3 .
```

These state that *book1* is a publication (1), *book2* is an article (2), articles are publications (3), things that are published are publications (4), and *MITPress* publishes *book3* (5).

We can use the following query to ask for a list of all publications:

```
SELECT ?pub WHERE {
  ?pub rdf:type ex:Publication .
}
```

The result, perhaps unexpectedly, only includes *book1*.  This seems strange because, as humanfolk, we can infer from the five triples that *book2* is a publication, since it's an article and articles are publications, and that *book3* is also a publication, since it's published by *MITPress* and things that are published are publications.

For Blazegraph to infer the same truths, special inference rules must be applied to compute and load new triples that directly represent the conclusions.  Blazegraph includes support for [RDFS entailment rules](http://www.w3.org/TR/2014/REC-rdf11-mt-20140225/#patterns-of-rdfs-entailment-informative).

The rule [*rdfs9*](http://www.w3.org/TR/rdf11-mt/#patterns-of-rdfs-entailment-informative) states:

> If S contains:
>
> ```
> xxx rdfs:subClassOf yyy .
> zzz rdf:type xxx .
> ```
>
> then S RDFS entails recognizing D:
>
> ```
> zzz rdf:type yyy .
> ```

This can be applied to the triples (3) and (2) to derive:

```
(6) ex:book2 rdf:type ex:Publication
```

The rule [*rdfs3*](http://www.w3.org/TR/rdf11-mt/#patterns-of-rdfs-entailment-informative) states:

> If S contains:
>
> ```
> aaa rdfs:range xxx .
> yyy aaa zzz .
> ```
>
> then S RDFS entails recognizing D:
>
> ```
> zzz rdf:type xxx .
> ```

This can be applied to (4) and (5) to derive:

```
(7) ex:book3 rdf:type ex:Publication .
```

The triples (6) and (7) can then be used to find that *ex:book2* and *ex:book3* are also publications using the query above.

## Walkthrough

Let's write our own schema, *http://www.example.org/#*, that includes `subClassOf` and `range`.  These shall mimic the semantics of their RDFS counterparts, [`rdfs:subClassOf`](http://www.w3.org/TR/rdf-schema/#ch_subclassof) and [`rdfs:range`](http://www.w3.org/TR/rdf-schema/#ch_range).

First, we write the Java version of our schema:

```java
package schemas;

import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;

public class EXAMPLE {

  public static final String NAMESPACE = "http://www.example.org/#";

  public static final URI SUBCLASSOF = new URIImpl(NAMESPACE + "subClassOf");
  public static final URI RANGE = new URIImpl(NAMESPACE + "range");

}
```

We need to incorporate it into a `Vocabulary`, so that various [internal runtime references](http://www.blazegraph.com/docs/api/com/bigdata/rdf/internal/impl/uri/VocabURIShortIV.html) can be computed:

```java
package schemas;

public class ExampleVocabulary extends DefaultBigdataVocabulary {

  public ExampleVocabulary() {
    super();
  }

  public ExampleVocabulary(final String namespace) {
    super(namespace);
  }

  @Override
  protected void addValues() {
    super.addValues();
    addDecl(new BaseVocabularyDecl(
        EXAMPLE.SUBCLASSOF,
        EXAMPLE.RANGE
    ));
  }
}
```

To let Blazegraph know about this vocabulary, we add it to our [configuration file](http://wiki.bigdata.com/wiki/index.php/GettingStarted#Ok.2C_I.E2.80.99ve_picked_the_bigdata_configuration_setting_I_want_to_work_with._Help_me_write_some_code.):

*exampleclosure.properties:*

```
com.bigdata.rdf.store.AbstractTripleStore.vocabularyClass=schemas.ExampleVocabulary
```

Now we can write a `Rule` (that looks suspiciously similar to [`RuleRdfs09`](http://www.blazegraph.com/docs/api/com/bigdata/rdf/rules/RuleRdfs09.html)):

```java
package rules;

public class RuleEx09 extends Rule {
  public RuleEx09(String relationName, Vocabulary vocab) {
    super("ex09", new SPOPredicate(relationName, var("v"),
        vocab.getConstant(RDF.TYPE), var("x")),
        new SPOPredicate[] {
            new SPOPredicate(relationName, var("u"), vocab.getConstant(EXAMPLE.SUBCLASSOF), var("x")),
            new SPOPredicate(relationName, var("v"), vocab.getConstant(RDF.TYPE), var("u"))
        },
        new IConstraint[] { Constraint.wrap(new NE(var("u"), var("x"))) });
  }
}
```

We include this rule in our own `FullClosure` implementation:

```java
package rules;

public class ExampleClosure extends FullClosure {

  public FullerClosure(AbstractTripleStore db) {
    super(db);
  }

  @Override
  protected List<Rule> getCustomRules(String database) {
    List<Rule> customRules = new ArrayList<Rule>();
    customRules.addAll(super.getCustomRules(database));
    customRules.add(new RuleEx09(database, vocab));
    return customRules;
  }
}
```

And we tell Blazegraph to use `ExampleClosure` via our configuration file:

*exampleclosure.properties:*

```
com.bigdata.rdf.store.AbstractTripleStore.closureClass=rules.ExampleClosure
```

Finally, we can load up the configuration and create a new repository instance:

```java
Properties properties = new Properties();
String resource = "/exampleclosure.properties";
InputStream propertiesStream = getClass().getResourceAsStream(resource);
properties.load(propertiesStream);

File jnl = File.createTempFile("bigdata", ".jnl");
properties.setProperty(BigdataSail.Options.FILE, jnl.getAbsolutePath());

BigdataSail sail = new BigdataSail(properties);
Repository repo = new BigdataSailRepository(sail);
repo.initialize();
```

## References

* http://wiki.bigdata.com/wiki/index.php/InferenceAndTruthMaintenance
* http://wiki.bigdata.com/wiki/index.php/GettingStarted
* http://www.w3.org/TR/2009/WD-sparql11-entailment-20091022/
* http://www.w3.org/TR/2014/REC-rdf11-mt-20140225/
* *Harth, Andreas. Linked Data Management. pp. 222-224.*
