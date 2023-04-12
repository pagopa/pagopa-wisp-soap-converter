//import React from "./libs/react.js";
//import htm from "./libs/htm.module.js";
//const html = htm.bind(React.createElement);

function Configuration(props) {

    const [configurations, setConfigurations] = React.useState([]);
    const [filter, setFilter] = React.useState("");

    React.useEffect(()=>{
        props.setLoading(true)
        fetch(`${baseUrl}/config`)
        .then(data=>data.json())
        .then(cl => {
            setConfigurations(cl.sort((s,d)=>(s.category+s.key).localeCompare((d.category+d.key))))
            props.setLoading(false)
        })
    },[])

    return html`<div>
        <p className="span2">Filtro key/value: <input type="text" value=${filter} onChange=${(e)=>setFilter(e.target.value.toLowerCase())}/></p>
        <hr/>
        <div className="gridConfig tabled">
                <span className="alignleft header">Type</span>
                <span className="alignleft header">Key</span>
                <span className="alignleft header">Value</span>
                ${configurations.filter(s=>{
                    return s.key.toLowerCase().indexOf(filter)>-1 || s.value.toLowerCase().indexOf(filter)>-1
                }).map(cfg=>{
                    return html`<${React.Fragment} key=${cfg.category+cfg.key}>
                        <span className="alignleft">${cfg.category}</span>
                        <span className="alignleft"><b>${cfg.key}</b><i className="descr">${cfg.description}</i></span>
                        <span className="alignleft ellipsis">${cfg.value}</span>
                        </${React.Fragment}>`
                })}
            </div>
    </div>`;
  }

//export default Configuration