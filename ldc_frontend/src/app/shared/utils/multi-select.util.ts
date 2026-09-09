/**
 * "Tout sélectionner" pour un mat-select multiple dont le FormControl vaut
 * '' (défaut non touché) ou un tableau d'ids sélectionnés.
 */

export function isAllSelected<T>(current: T[] | '', allIds: T[]): boolean {
  const values = current || [];
  return (
    allIds.length > 0 &&
    values.length === allIds.length &&
    allIds.every(id => values.includes(id))
  );
}

export function toggleAllSelection<T>(current: T[] | '', allIds: T[]): T[] {
  return isAllSelected(current, allIds) ? [] : allIds;
}
