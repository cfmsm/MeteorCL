package com.github.cfmsm.meteorcl.highlevel;

import com.github.cfmsm.meteorcl.*;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class ResultAsync extends Result {

    private final MeteorCL ctx;
    private final DataBuffer input;
    private final ComputePipeline pipeline;
    private final CommandBatch batch;
    private final long fence;

    public ResultAsync(
            MeteorCL ctx,
            DataBuffer input,
            ComputePipeline pipeline,
            CommandBatch batch,
            long fence
    ) {
        super(null);
        this.ctx = ctx;
        this.input = input;
        this.pipeline = pipeline;
        this.batch = batch;
        this.fence = fence;
    }

    public Result await() {

        batch.waitFence(fence, Long.MAX_VALUE);
        ctx.destroyFence(fence);

        ByteBuffer raw =
                input.read((int) input.size, 0);

        pipeline.destroy();

        return new Result(raw);
    }

    public CompletableFuture<Result> future() {
        return CompletableFuture.supplyAsync(this::await);
    }
}