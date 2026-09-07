/**
 * The `?category=` value that asks for `category_code: null`. Not the empty string, because that is
 * already taken by "no filter at all" — and the distinction is the point: the proposal engine
 * treats null as one shared bucket, so uncategorised entries are a group a user can ask for, not
 * an absence to be hidden.
 *
 * It sits outside both the rail that writes it and the list that reads it, because they are the two
 * halves of one axis and a second spelling of it would silently split them.
 */
export const NO_CATEGORY = 'NONE'
