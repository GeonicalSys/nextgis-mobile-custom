---
id: android-20261030-selected-track-share-binding
area: Мобильный UI и контракты треков
kind: internal
title: Привязка Share к выбранному общему track ID и адресные регрессии
why: Host связывает новый action с прежним exporter без подмены ID и stale запуска.
covers:
- app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
- app/src/test/java/com/nextgis/mobile/fragment/SelectedTrackShareBindingTest.java
- app/src/test/java/com/nextgis/maplibui/util/TrackGpxNameExportTest.java
- app/src/test/java/com/nextgis/maplibui/view/TrackSelectionPanelTest.java
- app/AGENTS.md
- app/docs/README.md
- app/docs/manifest.yaml
- docs/architecture/track-controls.md
- docs/architecture/shared-tracks.md
- docs/architecture/map-rendering.md
- docs/architecture/ui-accessibility.md
- docs/architecture/ui-contracts.md
- docs/reference/official-differences.md
- docs/roadmap/mobile.md
- docs/registry/ui-contracts.json
- docs/registry/invariants.yaml
- docs/registry/smoke-tests.yaml
- docs/registry/change-impact.yaml
at: '2026-10-31T02:07:35.102540+00:00'
---

### Поделиться выбранным треком

В нижней панели выбранного трека доступно «Поделиться». Действие передаёт один
shared track ID в тот же GPX exporter, которым пользуется список треков. Выбор
на карте не является FID векторного объекта, ID слоя или текущим сеансом записи.
Скрытый выбранный трек сохраняет действие; отправляется сохранённая история,
без остановки записи и без обещания дописать ещё не сохранённый хвост.

Непосредственно перед запуском проверяются текущие Activity, view, selected ID
и обычный режим карты. Список по-прежнему умеет выбирать несколько треков и
использует прежний вопрос об одном/отдельных GPX. XML-valid имя сохраняется в
GPX без потери Unicode, XML metacharacters и CR/LF/tab; path separators меняют
только basename отдельного файла. Обычные имена файлов и GPX segments/order
сохранены. Intent, FileProvider, MIME и read grant остаются у прежнего exporter.

Панель распределяет пять действий по `floor(available/48dp)` колонкам, от одной
до пяти, перед внешним fitting. Это ограничение горизонтального unscaled target,
а не обещание вписать весь map stack: очень узкий header title/Close и scaling
короткого окна требуют отдельной проверки. Адресные JVM проверки различают
actual binding, exporter и CPU-font widget layout; device/touch/OEM/receiver и
установленный APK не принимаются по этим моделям.

Source-JVM outcomes записываются Root только по собственным XML/log/input/stop receipts; pending не означает PASS.


Actual acceptance is compositional: Binding8 andExporter10 all18PASS inearlierfirstgreen23 on637a; itsfourPaneloverlapfailures werekept. Diagnostic-only64ac changedfailuremessage/helper only, notfivepredicates/oracles/setup.31Octactualcohort10 hadCaption5PASS+Panel1PASS4FAIL andexit1, NOTwhole10PASS; observedRename/Visibilityrowoverflow3px. Oneproductionline disablesbaselinealignment onlyownedactionRows. FinalONLYsamePanel5/64ac on0069 PASS0fail/errors/skips, XML19.913s, Gradle0/BUILD1m17s,81.6374157sobserved,120unchangedinputs/all13ownedsettledabsent. Thus23distinctmethodsacceptedACROSS18+5batches; noonefinal23rerun, no18/Caption5rerun, noMIME2executionclaim. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c.; CPUfont/JVM isnotnativeAndroid/device/OEM/wholewindow/GPU/recipientacceptance.

Baseline23=18assertFAIL5controls/0errors/skips:13missingShareID assertionsacrossBinding8/Panel5 arefeatureabsence, NOTproofoldstalelaunchbugs; Exporter5XML/pathcounterexamples+5controls independentlydemonstrateactualdefects. Firstgreen23=19PASS4PaneloverlapFAIL, XML11.393s,Gradle1; nofullPASS. DiagnosticV3 9ec/523f LFops/CRLFcandidate rejectedBEFOREC/runtime byRootguard; retainedinvalidhistory. CorrectedRAWV4 c093/64ac exactreplay/inverse78eff, Bridge73edf; onlymessage/helper changed. Diagnostic10 captured3pxbaselineoffset, one-line637a→0069 preservesIDs/callbacks/copy/widthlogic/all5oracles; finalPanel5accepted. Oldmessage89/selector37/cache29/caption5 resultsremain separatehistoricalcohorts, notrerun/resummed.


Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c.
