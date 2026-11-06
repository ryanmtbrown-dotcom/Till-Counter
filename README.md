# Till Counter

Clean native Android till-counting app. Offline; no permissions, network, WebView, HTML or JavaScript.

Workflow: Store Charges → Gift Certificates → Vendor Coupons → Checks → Loans → Cash denominations → Till Summary.

Counting cash: tap C to clear an unaccepted entry; long-press C to reset the saved count for the selected denomination (or the selected loose/roll mode). The final form displays a cash drop recommendation using available counted denominations, followed by Cash, Checks, Vendor Coupon, Gift Certificates, Store Charges, and Loans. The physical drop may be less than the target if exact change is unavailable; that difference is shown explicitly.

Release proof: native Android 7 form-value checks read paired labels and values after resetting the scroll position; Android 16 instrumentation also resets summary scrolling before verification. Device-test time budgets are bounded separately from the overall 45-minute job limit.

## v1.4.0 counting improvements

On Store Charges, Gift Certificates, Vendor Coupons, Checks, and Loans, use the **−** keypad key to toggle the pending amount between positive and negative. Positive and negative entries both contribute to the stage subtotal and Till Summary; the base-till setting stays non-negative.

Bills have independent **LOOSE**, **STRAPS**, and **BUNDLES** quantity modes. One U.S. currency strap contains 100 notes of the selected denomination, and one bundle contains 1,000 notes (10 straps). The denomination and active entry mode appear directly above the keypad. Switching modes preserves the accepted quantities; the total cash and available cash-drop inventory include all three. Coins retain separate loose and roll counts. Tap C to clear the pending entry, or long-press C to clear the accepted count of the selected mode.
