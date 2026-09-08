import { isAllSelected, toggleAllSelection } from './multi-select.util';

describe('multi-select.util', () => {
  describe('isAllSelected', () => {
    it('returns false when nothing is selected (untouched FormControl default)', () => {
      expect(isAllSelected('', [1, 2, 3])).toBeFalse();
    });

    it('returns false when only some options are selected', () => {
      expect(isAllSelected([1, 2], [1, 2, 3])).toBeFalse();
    });

    it('returns true when every available option is selected', () => {
      expect(isAllSelected([1, 2, 3], [1, 2, 3])).toBeTrue();
    });

    it('returns false when the list of available options is empty', () => {
      expect(isAllSelected([], [])).toBeFalse();
    });

    it('returns false when the current selection has the same size as the available options but different ids (stale selection after the options changed)', () => {
      expect(isAllSelected([1, 2], [3, 4])).toBeFalse();
    });
  });

  describe('toggleAllSelection', () => {
    it('selects every option when not all are currently selected', () => {
      expect(toggleAllSelection([1], [1, 2, 3])).toEqual([1, 2, 3]);
    });

    it('selects every option when starting from the untouched default', () => {
      expect(toggleAllSelection('', [1, 2, 3])).toEqual([1, 2, 3]);
    });

    it('clears the selection when every option is already selected', () => {
      expect(toggleAllSelection([1, 2, 3], [1, 2, 3])).toEqual([]);
    });
  });
});
