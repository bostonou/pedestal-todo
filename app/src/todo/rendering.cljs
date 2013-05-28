(ns todo.rendering
  (:require [domina :as dom]
            [domina.css :as dc]
            [domina.events :as de]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msgs]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]            
            [io.pedestal.app.render.events :as evts]
            [io.pedestal.app.render.push.handlers :as h]
            [io.pedestal.app.render.push.handlers.automatic :as d])
  (:require-macros [todo.html-templates :as html-templates]))

;; Load templates.

(def templates (html-templates/todo-templates))

(defn render-todo [renderer [_ path] transmitter]  
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path "todoapp")        
        html (templates/add-template renderer path (:todo-page templates))]    
    (dom/append! (dom/by-id parent) (html))))

(defn destroy-task [renderer [_ path] transmitter]  
  (dom/destroy! (dom/by-id (render/get-id renderer path))))

(defn render-tasks [renderer [_ path] transmitter]
  (render/new-id! renderer path "todo-list"))

(defn render-task-filter [_ [_ _ _ filter] _]
  (dom/remove-class! (dc/sel "#filters a") "selected")
  (cond
   (= filter :any)
   (do (dom/set-style! (dc/sel "#todo-list li")  :display "block")
       (dom/add-class! (dc/sel "#all-filter") "selected")
       )
   (= filter :completed)
   (do
     (dom/add-class! (dc/sel "#completed-filter") "selected")
     (dom/set-style! (dc/sel "#todo-list li")  :display "none")
     (dom/set-style! (dc/sel "#todo-list .completed")  :display "block"))
   (= filter :active)
   (do
     (dom/add-class! (dc/sel "#active-filter") "selected")
     (dom/set-style! (dc/sel "#todo-list li")  :display "block")
     (dom/set-style! (dc/sel "#todo-list .completed")  :display "none"))))
  

(defn render-task-count [renderer [_ path] transmitter]
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path)        
        html (templates/add-template renderer path (:task-count templates))]    
    (dom/append! (dom/by-id parent) (html {:id id}))))

(defn render-task [renderer [_ path] transmitter]
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path)        
        html (templates/add-template renderer path (:task templates))]    
    (dom/append! (dom/by-id parent) (html {:id id}))))

(defn render-task-value [renderer [_ path _ new-task] transmitter]  
  )

(defn render-task-details [renderer [_ path _ new-details] transmitter]
  (templates/update-parent-t renderer path {:details new-details}))

(defn render-task-completed [renderer [_ path _ completed] transmitter]
  ;; Need to get the parent's id, because that is where the class needs to be placed
  (let [task-container (dom/by-id (str (render/get-parent-id renderer path)))
        task-checkbox (dc/sel (str "#" (render/get-parent-id renderer path) " input"))
        class-fn (if completed dom/add-class! dom/remove-class!)]    
    (set! (.-checked (dom/single-node task-checkbox)) completed)    
    (class-fn task-container "completed")))

(defn render-task-count-value [renderer [_ path _ new-value] transmitter]
  (if (> new-value 0)
    (dom/set-style! (dom/by-id "toggle-all") :display "block")
    (dom/set-style! (dom/by-id "toggle-all") :display "none"))
  (dom/set-text! (dc/sel (str "#todo-count strong")) (str new-value)))

(defn render-task-completed-value [renderer [_ path _ new-value] transmitter]    
  (dom/set-text! (dc/sel (str "#clear-completed span")) (str new-value)))

(defn render-message [renderer [_ path _ new-value] transmitter]
  (templates/update-t renderer path {:details (:details new-value) :completed (str (:completed new-value))}))

;; need to import domina.events, and dom.css if you want to use the sel syntax
;; send-on can use domina object which is dom/DomContent

;; Using add-send-on-click
;; Don't have to manually set up the send-on with the input-queue and msgs
;; We know it's a click, just need to specify the domina element
;; Passing a string means it's automatically going to look up the ID

;; Make sure when creating lists of vectors, they are actually lists or dictionaries.
;; and not three separate vectors

;; Early bug had me doing [:node-create [:tasks] :map]
;; This was creating everything, but technically, the first node create was not being sent

;; Need to add key down handlers

;; msg/fill can add details to many related messages
;; m ust pass each individual message to the input queue

;; The initial :todo render page should load the main template
;; This will specify all the locations where everything should go

;; Will probably need another way to specify render config dependencies once you have
;; something fairly large.  

(defn render-config []  
  [[:node-create  [:todo] render-todo]
   [:node-create  [:todo :tasks] render-tasks]
   [:node-destroy [:todo :tasks :*] destroy-task]   
   [:node-create  [:todo :tasks :*] render-task]
   [:value  [:todo :tasks :*] render-task-value]
   [:value  [:todo :tasks :* :details] render-task-details]
   [:value  [:todo :tasks :* :completed] render-task-completed]
   [:value [:todo :filter] render-task-filter]
   [:transform-enable [:todo] (h/add-send-on-click "toggle-all")]
   [:transform-enable [:todo :filter] (fn [r [_ p k messages] input-queue]
                                        
                                        (evts/send-on :click (dom/by-id "all-filter")
                                                      input-queue
                                                      (msgs/fill :set-filter messages {:filter :any}) )
                                        (evts/send-on :click (dom/by-id "active-filter")
                                                      input-queue
                                                      (msgs/fill :set-filter messages {:filter :active}) )
                                        (evts/send-on :click (dom/by-id "completed-filter")
                                                      input-queue
                                                      (msgs/fill :set-filter messages {:filter :completed}) ))]
   [:transform-enable [:todo :tasks :*] (fn [r [_ p k messages] input-queue]
                                          
                                          (cond
                                           (= k :toggle-task)
                                           #_(de/listen! (dc/sel (str "#" (render/get-id r p) " input")) :click
                                                       (fn [_]
                                                         (.log js/console (pr-str k))
                                                         (.log js/console (pr-str messages))
                                                         )
                                                       )
                                           (evts/send-on :click (dc/sel (str "#" (render/get-id r p) " input")) input-queue messages )
                                           (= k :remove-task)
                                           #_(de/listen! (dc/sel (str "#" (render/get-id r p) " .destroy")) :click
                                                       (fn [_]
                                                         (.log js/console (pr-str k))
                                                         (.log js/console (pr-str messages))
                                                         )
                                                       )
                                           (evts/send-on :click (dc/sel (str "#" (render/get-id r p) " .destroy")) input-queue messages ))
                                          )]
   [:transform-enable [:todo :tasks] (fn [r [_ p k messages] input-queue]                                       
                                       (cond
                                        (= k :add-tasks)
                                        (let [todo-input (dom/by-id "new-todo")]
                                          (de/listen! todo-input :keydown
                                                      (fn [e]                                                
                                                        (when (= (.-keyCode (.-evt e)) 13)
                                                          (let [details (dom/value todo-input)
                                                                new-msgs (msgs/fill :add-task messages {:details details})]
                                                            (dom/set-value! todo-input "")
                                                            (doseq [m new-msgs]
                                                              (p/put-message input-queue m)))
                                                          ))))
                                        (= k :clear-completed)
                                        (do
                                          (de/listen! (dom/by-id "clear-completed") :click
                                                      (fn [_]
                                                        (dom/destroy! (dc/sel "#todo-list .completed"))))
                                          (evts/send-on :click (dom/by-id "clear-completed") input-queue messages ))))]
   [:value [:todo :count] render-task-count-value]
   [:value [:todo :completed-count] render-task-completed-value]   
   [:node-destroy   [:todo] d/default-exit]])


;; Add a visible property
;; Or, add a [todo visible-tasks]
;; 
