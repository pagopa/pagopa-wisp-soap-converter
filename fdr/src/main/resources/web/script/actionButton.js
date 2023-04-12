//import React from "./libs/react.js";
//import htm from "./libs/htm.module.js";
//const html = htm.bind(React.createElement);

function ActionButton(props) {
    const [isSending, setIsSending] = React.useState(false)
    const [tooltip, setTooltip] = React.useState({})
    const sendRequest = React.useCallback(async () => {
        if (isSending) return
        setIsSending(true)
        let resp = await fetch(props.url).then(d=>d.text()).then(t=>{
            try{
                return {text:JSON.parse(t).description,success:true}
            }catch(e){
                return {text:t,success:true}
            }
        }).catch(e=>{
            return {text:e.message,success:false}
        })
        if(props.callback){
            props.callback()
        }
        setTooltip(resp)
        setIsSending(false)
        clearTooltip()
    }, [isSending])

    const clearTooltip = React.useCallback(async () => {
        setTimeout(()=>{ setTooltip('') },2000)
    }, [isSending])

    return html`<div className="buttonContainer">
        <input type="button" className="button doAction ${props.small?'small':''}" disabled=${isSending} onClick=${sendRequest} value=${props.text}/>
        ${tooltip.success!=undefined?html`<div className="tooltip ${!tooltip.success?'error':''}">${tooltip.text}</div>`:''}
    </div>`
}

//export default ActionButton