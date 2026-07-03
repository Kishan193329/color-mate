"""ColorForge AI processing services.

Each module owns one stage of the pipeline:

    image_preprocess  -> normalise / resize the uploaded image
    lineart_generator -> produce clean line art
    gap_closer        -> close small gaps so regions are flood-fillable
    region_detector   -> find closed regions and assign colors/numbers
    mask_generator    -> render a per-region color mask
    svg_generator     -> vector outline of the regions
    json_generator    -> regions.json / game_config.json / palette.json
    preview_renderer  -> a friendly preview image
    zip_exporter      -> bundle everything into result.zip

For Sprint 1 every stage returns deterministic mock/demo output, but each
function signature is the one the real implementation will use.
"""
