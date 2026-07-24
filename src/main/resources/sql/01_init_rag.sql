-- AssistAgent RAG 数据库初始化脚本
-- 在 assist_agent 数据库中手动执行一次。
-- embedding 列维度必须与 application.yml 中配置的 1536 保持一致。

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;

CREATE TABLE IF NOT EXISTS public.assist_vector_store (
    id        uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);

COMMENT ON TABLE public.assist_vector_store IS 'RAG 文档切片及其向量数据';
COMMENT ON COLUMN public.assist_vector_store.id IS '文档切片唯一标识，使用稳定 UUID 支持幂等更新';
COMMENT ON COLUMN public.assist_vector_store.content IS '用于向量化和检索的文档切片正文';
COMMENT ON COLUMN public.assist_vector_store.metadata IS '文档来源、版本、章节、条款和切片序号等 JSON 元数据';
COMMENT ON COLUMN public.assist_vector_store.embedding IS 'Embedding 模型生成的 1536 维向量';

CREATE INDEX IF NOT EXISTS assist_vector_store_index
    ON public.assist_vector_store
    USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS public.rag_source_manifest (
    collection      text        NOT NULL,
    source          text        NOT NULL,
    checksum        varchar(64) NOT NULL,
    index_signature text        NOT NULL,
    chunk_count     integer     NOT NULL,
    indexed_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection, source)
);

COMMENT ON TABLE public.rag_source_manifest IS 'RAG 源文件索引状态清单，用于增量同步和避免重复向量化';
COMMENT ON COLUMN public.rag_source_manifest.collection IS '知识库集合标识，用于隔离不同知识库的数据';
COMMENT ON COLUMN public.rag_source_manifest.source IS '源文档文件名或资源路径';
COMMENT ON COLUMN public.rag_source_manifest.checksum IS '源文档内容的 SHA-256 校验值';
COMMENT ON COLUMN public.rag_source_manifest.index_signature IS 'Embedding 模型、维度、切分参数和索引版本组成的索引签名';
COMMENT ON COLUMN public.rag_source_manifest.chunk_count IS '该源文档最近一次成功写入的切片数量';
COMMENT ON COLUMN public.rag_source_manifest.indexed_at IS '该源文档最近一次成功完成索引的时间';

COMMIT;
