(ns duct.core-test
  (:require [clojure.test :refer :all]
            [duct.core :as core]
            [duct.core.merge :as merge]
            [integrant.core :as ig]))

(deftest test-add-shutdown-hook
  (let [f #(identity true)
        hooks (core/add-shutdown-hook ::foo f)]
    (is (= f (::foo hooks)))))

(deftest test-remove-shutdown-hook
  (core/add-shutdown-hook ::foo #(identity true))
  (let [hooks (core/remove-shutdown-hook ::foo)]
    (is (nil? (::foo hooks)))))

(derive ::aa ::a)
(derive ::ab ::a)
(derive ::ab ::b)

(deftest test-merge-configs
  (are [a b c] (= (core/merge-configs a b) c)
    {::a 1}         {::a 2}                       {::a 2}
    {::a {:x 1}}    {::a {:y 2}}                  {::a {:x 1 :y 2}}
    {::a {:x 1}}    {::a ^:displace {:x 2}}       {::a {:x 1}}
    {}              {::a ^:displace {:y 2}}       {::a {:y 2}}
    {::aa 1}        {::a 2}                       {::aa 2}
    {::aa 1 ::ab 2} {::a 3}                       {::aa 3 ::ab 3}
    {::aa {:x 1}}   {::a {:y 2}}                  {::aa {:x 1 :y 2}}
    {::a 1}         {::aa 2}                      {::aa 2}
    {::a {:x 1}}    {::aa {:y 2}}                 {::aa {:x 1 :y 2}}
    {::a {:x 1}}    {::aa {:y 2} ::ab {:z 3}}     {::aa {:x 1 :y 2} ::ab {:x 1 :z 3}}
    {::a 1}         {::a (merge/displace 2)}      {::a 1}
    {::a {:x 1}}    {::a {:x (merge/displace 2)}} {::a {:x 1}}
    {::a [:x :y]}   {::a [:y :z]}                 {::a [:x :y :y :z]}
    {::a [:x :y]}   {::a ^:distinct [:y :z]}      {::a [:x :y :z]}))

(derive ::xx ::x)

(derive ::mod1 :duct/module)
(derive ::mod2 :duct/module)
(derive ::mod3 :duct/module)

(defmethod ig/init-key ::x [_ x] x)

(defmethod ig/init-key ::mod1 [_ _]
  {:fn (fn [cfg] (assoc cfg ::xx 1))})

(defmethod ig/init-key ::mod2 [_ _]
  {:req #{::xx}, :fn (fn [cfg] (assoc cfg ::y (inc (::xx cfg))))})

(defmethod ig/init-key ::mod3 [_ _]
  {:req #{::x ::y}, :fn (fn [cfg] (assoc cfg ::z (+ (::xx cfg) (::y cfg))))})

(deftest test-prep
  (testing "valid modules"
    (let [config {::mod1 {}, ::mod2 {}, ::mod3 {}}]
      (is (= (core/prep config)
             (merge config {::xx 1, ::y 2, ::z 3})))))

  (testing "missing requirements"
    (let [config {::mod2 {}, ::mod3 {}}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           (re-pattern
            (str "Missing module requirements: "
                 ::mod2 " requires \\(" ::xx "\\), "
                 ::mod3 " requires \\(" ::x " " ::y "\\)"))
           (core/prep config))))))

(deftest test-environment-keyword
  (let [m {::core/environment :development}]
    (is (= m (ig/init m)))))

(deftest test-project-ns-keyword
  (let [m {::core/project-ns 'foo}]
    (is (= m (ig/init m)))))
