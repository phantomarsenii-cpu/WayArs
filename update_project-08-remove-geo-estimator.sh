#!/usr/bin/env bash
# WayArs — Update 08: удалён неиспользуемый GeoDistanceEstimator.kt (заготовка расчёта расстояния через
# публичные серверы Nominatim/OSRM). Нигде не вызывался; после удаления в коде нет адресов сторонних
# серверов, что соответствует политике конфиденциальности ("обработка на устройстве").
# ТОЛЬКО для WayArs. Запускать из КОРНЯ репозитория WayArs.
set -euo pipefail

P=app/src/main/java/com/wayars/app
[ -f "$P/WayArsApplication.kt" ] || { echo "СТОП: это не репозиторий WayArs (нет $P/WayArsApplication.kt)"; exit 1; }

F="$P/util/GeoDistanceEstimator.kt"
if [ ! -f "$F" ]; then
  echo "уже применено (файла нет)"
  exit 0
fi

# Страховка: если вдруг где-то появилось использование — не удаляем, чтобы не сломать сборку.
if grep -rIn "GeoDistanceEstimator\|LatLng" --include=*.kt app/src | grep -v "^$F:" | grep -q .; then
  echo "СТОП: найдены ссылки на GeoDistanceEstimator/LatLng в другом коде:"
  grep -rIn "GeoDistanceEstimator\|LatLng" --include=*.kt app/src | grep -v "^$F:"
  exit 1
fi

rm "$F"
echo "удалён: $F"
