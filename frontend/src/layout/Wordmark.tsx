import markDark from '../assets/mark-dark.png'
import markLight from '../assets/mark-light.png'

/** The app's name with the brand mark, shared by the app header and the landing page. Both marks ship;
 *  CSS shows the one drawn for the palette in force, which the landing page pins to light. */
export function Wordmark() {
  return (
    <div className="wordmark">
      <img className="mark mark-light" src={markLight} alt="" />
      <img className="mark mark-dark" src={markDark} alt="" />
      <span>2do AI</span>
    </div>
  )
}
