/*******************************************************************************************************
 *
 * Predicate.java, in gama.extension.bdi, is part of the source code of the GAMA modeling and simulation
 * platform .
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package gama.plugin.types;

import java.nio.file.Path;
import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.misc.IValue;
import gama.api.utils.json.IJson;
import gama.api.utils.json.IJsonValue;
import gama.core.util.json.Json;
import gama.core.util.json.JsonValue;

/**
 * The Class Predicate.
 */
public class ContentRetriever implements IValue {
 
	dev.langchain4j.rag.content.retriever.ContentRetriever contentRetriever = null;
	
	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType(), "content_retriever", contentRetriever);
	}

	public ContentRetriever(Path path) {
		List<Document> documents = null;
		documents = FileSystemDocumentLoader.loadDocuments(
		          path
		    ); 
		if (documents!=null && documents.size() > 0) {
			

			// Here, we create an empty in-memory store for our documents and their
			// embeddings.
			InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
			// Here, we are ingesting our documents into the store.
			// Under the hood, a lot of "magic" is happening, but we can ignore it for now.
			EmbeddingStoreIngestor.ingest(documents, embeddingStore);
			
			// Lastly, let's create a content retriever from an embedding store.
			contentRetriever = EmbeddingStoreContentRetriever.from(embeddingStore);
			
			
		}
	}




	@Override
	public String toString() { 
		return "content_retriever(" + contentRetriever.toString() +")";
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return toString();
	}

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		return toString();
	}

	

	@Override
	public int hashCode() {
		return contentRetriever.hashCode();
	} 

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		final ContentRetriever other = (ContentRetriever) obj;
		return contentRetriever.equals(other.contentRetriever); 
	}

	/**
	 * Method getType()
	 *
	 * @see gama.core.common.interfaces.ITyped#getGamlType()
	 */
	@Override
	public IType<?> getGamlType() { return Types.get(ContentRetrieverType.id); }



	/*public static PathMatcher glob(String glob) {
		return FileSystems.getDefault().getPathMatcher("glob:" + glob);
	}*/



	@Override
	public IValue copy(IScope scope) throws GamaRuntimeException {
		// TODO Auto-generated method stub
		return null;
	}

}
