// Project logic only. Selection, date arithmetic, validation and limits belong to the APK host API.
function run(ctx) {
    if (!ctx.feature.isNew) return;
    const contractor = ctx.feature.get("contractor");
    if (typeof contractor !== "string" || !contractor.trim()
            || contractor.trim().toLocaleLowerCase("ru") === "нет значения") return;
    const range = ctx.time.monthWindow();
    const layers = ["audit_vyvozka", "audit_roads", "audit_logging", "audit_forestry",
        "audit_survey", "audit_office", "audit_security", "audit_transport",
        "audit_fire", "audit_terminal"];
    for (const layer of layers) {
        const result = ctx.gis.query(layer, {
            fields: ["date_audit"], limit: 1,
            where: [
                {field: "contractor", op: "eq", value: contractor},
                {field: "issue", op: "eq", value: "Да"},
                {field: "date_audit", op: "gte", value: range.from},
                {field: "date_audit", op: "lt", value: range.until}
            ]
        });
        if (result.features.length) {
            ctx.warn("prior-issues", "У выбранного подрядчика за последний месяц уже были аудиты с замечаниями. "
                + "Учитывайте их при проведении текущего аудита. Проверена история, загруженная на телефон.");
            return;
        }
    }
}
