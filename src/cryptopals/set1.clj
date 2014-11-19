(ns cryptopals.set1)

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(defmacro bit-ops
  []
  `(do
     ~@(for [[s l] '[[<< bit-shift-left]
                     [>> bit-shift-right]
                     [>>> unsigned-bit-shift-right]
                     [| bit-or]
                     [& bit-and]
                     [xor bit-xor]
                     [!b bit-not]]]
         `(do
            (def ~s ~(deref (resolve l)))
            (.setMeta (var ~s) (meta ~(resolve l)))))))
(bit-ops)

(defmacro dbg [x]
  `((fn [y#]
      (println "Form: " '~(second &form))
      (println "Result: " y#)
      y#) ~x))

(def hex->bytes*
  (let [nibl (fn [chr]
               (case chr
                 (\0 \1 \2 \3 \4 \5 \6 \7 \8 \9) (- (int chr) (int \0))
                 (\a \b \c \d \e \f) (+ 10 (- (int chr) (int \a)))
                 (throw (IllegalArgumentException. (str "invalid hexidecimal character: " chr)))))
        to-byte (fn [[h l]]
                  (+ (* 16 (nibl h)) (nibl l)))]
    (comp (partition-all 2)
          (map to-byte))))

(def bytes->hex*
  (let [xnibl (fn [n]
                (when (or (neg? n)
                          (> n 15))
                  (throw (IllegalArgumentException. (str "nibble out of range: " n))))
                (cond
                 (< n 10)
                 (char (+ n (int \0)))

                 :else (char (+ (- n 10) (int \a)))))
        hex (fn [b]
              [(xnibl (>>> b 4))
               (xnibl (& b 0xF))])]
    (mapcat hex)))

(def bytes->base64*
    (let [b64* (fn [b]
               (when (or (neg? b) (> b 63))
                 (throw (IllegalArgumentException. (str "invalid base64 number: " b))))
               (cond
                (< b 26)
                (char (+ b (int \A)))

                (< b 52)
                (char (+ (- b 26) (int \a)))

                (< b 62)
                (char (+ (- b 52) (int \0)))

                (= b 62)
                \+

                (= b 63)
                \/))
        b64 (fn [[b1 b2 b3]]
              (cond
               b3
               [(b64* (>>> b1 2))
                (b64* (| (<< (& b1 3) 4)
                         (>>> b2 4)))
                (b64* (| (<< (& b2 0xF) 2)
                         (>>> b3 6)))
                (b64* (& b3 0x3F))]

               b2
               [(b64* (>>> b1 2))
                (b64* (| (<< (& b1 3) 4)
                         (>>> b2 4)))
                (b64* (<< (& b2 0xF) 2))
                \=]

               :else
               [(b64* (>>> b1 2))
                (b64* (<< (& b1 3) 4))
                \=
                \=]))]
      (comp (partition-all 3)
            (mapcat b64))))

(defn sanitize-hex
  [^String hex]
  (.toLowerCase (if (odd? (count hex))
                  (str "0" hex)
                  hex)))

(defn hex->bytes
  [^String hex]
  (sequence hex->bytes* (sanitize-hex hex)))

(defn hex->base64
  [^String hex]
  (apply str (sequence (comp hex->bytes* bytes->base64*)
                       (sanitize-hex hex))))

(defn xor-hex
  [hex1 hex2]
  (when-not (= (count hex1) (count hex2))
    (throw (IllegalArgumentException. "Unequal input lengths")))
  (apply str (sequence (comp (map xor)
                             bytes->hex*)
                       (sequence hex->bytes* (sanitize-hex hex1))
                       (sequence hex->bytes* (sanitize-hex hex2)))))

;; From http://www.data-compression.com/english.html
(def letter-freq
  {\e 0.0651738
   \t 0.0124248
   \a 0.0217339
   \o 0.0349835
   \i 0.1041442
   \n 0.0197881
   \s 0.0158610
   \h 0.0492888
   \r 0.0558094
   \d 0.0009033
   \l 0.0050529
   \c 0.0331490
   \u 0.0202124
   \m 0.0564513
   \w 0.0596302
   \f 0.0137645
   \g 0.0008606
   \y 0.0497563
   \p 0.0515760
   \b 0.0729357
   \v 0.0225134
   \k 0.0082903
   \j 0.0171272
   \x 0.0013692
   \q 0.0145984
   \z 0.0007836
   \space 0.1918182})
 
(defn score-string
  [^String string]
  (if (some #(or (< (int %) 32)
                 (> (int %) 126))
            string)
    Double/POSITIVE_INFINITY
    (let [s (.toLowerCase string)
          letters (filter letter-freq s)
          freq (into {}
                     (for [[ch fq] (frequencies letters)]
                       [ch (double (/ fq (count letters)))]))
          total (reduce (fn [sum [ch lfq]]
                          (+ (/ (freq ch 0) lfq)
                             sum))
                        0 letter-freq)]
      (/ total (count letters)))))

(defn byte-xor
  [^String hex b]
  (apply str (sequence (comp hex->bytes*
                             (map (partial xor b))
                             (map char))
                       (sanitize-hex hex))))

(defn pick-string
  [^String hex]
  (rest (reduce (fn [[scr :as res] [s n]]
                  (let [new-scr (score-string s)]
                    (if (< new-scr scr)
                      [new-scr s n]
                      res)))
                (let [fst (byte-xor hex 0)]
                  [(score-string fst) fst (char 0)])
                (for [n (range 1 128)]
                  [(byte-xor hex n) (char n)]))))
