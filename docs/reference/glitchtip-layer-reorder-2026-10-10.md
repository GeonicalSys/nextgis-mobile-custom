---
title: GlitchTip 52 — падение завершения перетаскивания слоёв
type: reference
last_verified: 2026-10-11
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/ReorderedLayerViewAnimated.java
  - app/src/test/java/com/nextgis/mobile/fragment/ReorderedLayerViewAnimatedTest.java
---

# Падение списка слоёв

Issue 52 проекта NextGIS Mobile: один fatal event
`99de5fb8e6214981bbe8a36888cb34f3`, production 3.1.2.27+221,
source revision `65acae8d02bf06b6c6b632514b4adc6ff1eb586a`, Android 16.
Стек: AbsListView.FlingRunnable.endFling → onScrollStateChanged →
isScrollCompleted → ReorderedLayerViewAnimated.touchEventsEnded:176.
getViewForID(mMobileItemId) вернул null; следующий getTop завершил app.
По событию нельзя установить, ушла ли строка за экран или обновился список.

Фикс проверяет видимую строку и hover до анимации. Cancel завершает owning
adapter, восстанавливает доступную строку и очищает mobile IDs, hover, active
pointer, scrolling/waiting flags. Перестановки сохраняет прежний endDrag.
Отложенный pre-draw пропускает исчезнувшую соседнюю строку. Изменяются только
app и docs; библиотеки и их pointers не меняются.

`:app:testLisaDebugUnitTest --tests
com.nextgis.mobile.fragment.ReorderedLayerViewAnimatedTest` — **8 passed**,
без skips, Robolectric API 26/36: idle после fling с отсутствующей строкой,
cancel с видимой строкой, снятый adapter, обычное ожидание окончания прокрутки.
Device reorder/fling, карта после перестановки и повторный production event
не проверены. По прямому поручению пользователя Issue 52 отмечен `resolved`
как обработанный 2026-10-11 00:06:40 Europe/Warsaw. История события сохранена;
закрытие отчёта не означает выпуск и проверку production-фикса.

Lisa и Belka Release assemble — PASS. Это локальная сборочная проверка с
исключёнными Sentry upload tasks; APK не публикуется, версии не повышены.
Docs validator и 7 docs-tool tests — PASS.

Upstream сверён GitHub API: official app master
`f11d38f77e4caf1b569526c5f620b2ec513c5f9d` сохраняет незащищённый getTop.

Поставка: app Draft PR → Squash в my-maplibre → отдельно согласованный APK
выпуск → device smoke. Desktop PR #180 независим: GlitchTip 51 в QTiles и
согласованный portable-artifact contract. Выпуск APK/ZIP здесь не выполняется.
