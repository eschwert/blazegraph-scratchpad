package demos;

import java.util.LinkedList;
import java.util.List;

import org.openrdf.OpenRDFException;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.Update;
import org.openrdf.repository.RepositoryConnection;

public class Example2 implements Example {

	public void update(RepositoryConnection cxn) throws OpenRDFException {
		String insert = "" //
				+ "PREFIX ex: <http://www.example.org/#>\n                    "
				+ "INSERT {\n                                                 "
				+ "  ex:book1 rdf:type ex:Publication .\n                     "
				+ "  ex:book2 rdf:type ex:Article .\n                         "
				+ "  ex:Article rdfs:subClassOf ex:Publication .\n            "
				+ "  ex:publishes rdfs:range ex:Publication .\n               "
				+ "  ex:MITPress ex:publishes ex:book3 .\n                    "
				+ "} WHERE {}                                                 ";
		Update update = cxn.prepareUpdate(QueryLanguage.SPARQL, insert);
		update.execute();
	}

	public Iterable<BindingSet> query(RepositoryConnection cxn) throws OpenRDFException {
		String queryStr = "" //
				+ "PREFIX ex: <http://www.example.org/#>\n                    "
				+ "SELECT ?book WHERE {\n                                     "
				+ "  ?book rdf:type ex:Publication.\n                         "
				+ "}                                                          ";
		TupleQuery tupleQuery = cxn.prepareTupleQuery(QueryLanguage.SPARQL,
				queryStr);
		TupleQueryResult result = tupleQuery.evaluate();
		List<BindingSet> rows = new LinkedList<BindingSet>();
		while (result.hasNext()) {
			rows.add(result.next());
		}
		return rows;
	}

}
