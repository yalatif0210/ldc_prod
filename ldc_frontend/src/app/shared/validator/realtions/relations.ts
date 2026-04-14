export interface Relation {
  intrants: number;
  main_reagents: number[];
}

export const COBAS_5800_MAINS_REAGENTS = {
  MAIN_REAGENT_1: 4040317,
  MAIN_REAGENT_2: 4040136,
  MAIN_REAGENT_3: 40403172,
};

export const COBAS_5800_RELATIONS = [
  {
    intrants: 4040140,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_2
    ],
  },
  {
    intrants: 40401403,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 4040115,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1
    ],
  },
  {
    intrants: 40401401,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 40401402,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 40401404,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 4040126,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 404084,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 404123,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 4040125,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 4040132,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 4030521,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_2,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 4040079,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 4040131,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1
    ],
  },
  {
    intrants: 40401405,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_1,
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  },
  {
    intrants: 40403173,
    main_reagents: [
      COBAS_5800_MAINS_REAGENTS.MAIN_REAGENT_3
    ],
  }
];
