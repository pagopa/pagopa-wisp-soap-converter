//import React from "./libs/react.js";
//import htm from "./libs/htm.module.js";
//const html = htm.bind(React.createElement);

function Changelogs(props) {

    const [db, setDb] = React.useState("cfg");

    const [changelogs, setChangelogs] = React.useState({
        'online':[],'offline':[],'re':[],'wfesp':[]
    });

    React.useEffect(()=>{
        props.setLoading(true)
        fetch(`${baseUrl}/changelogs`)
        .then(data=>data.json())
        .then(cl => {
            setChangelogs(cl)
            props.setLoading(false)
        })
    },[])

    return html`<div className="monospace changelogs">
    <div className="tabs">
        <span onClick=${()=>setDb('online')} className="${db=='online'?'selected':''}">online</span>
        <span onClick=${()=>setDb('offline')} className="${db=='offline'?'selected':''}">offline</span>
        <span onClick=${()=>setDb('re')} className="${db=='re'?'selected':''}">re</span>
        <span onClick=${()=>setDb('wfesp')} className="${db=='wfesp'?'selected':''}">wfesp</span>
    </div>
    <hr/>
    <div key=${db} className="changelog">
                <div className="grid4 tabled">
                    <span className="alignleft header">ID</span>
                    <span className="alignleft header">File</span>
                    <span className="alignleft header">Data</span>
                    <span className="alignleft header">Hash</span>
                    ${changelogs[db] && changelogs[db].map(clog=>{
                        return html`
                            <span key=${clog.id} className="alignleft">
                                <i className="ellipsis">${clog.id}</i>
                            </span>
                            <span key=${clog.file} className="alignleft">
                            <i className="ellipsis">${clog.file}</i></span>
                            <span key=${clog.date} className="alignleft" title=${new Intl.RelativeTimeFormat('en', { style: 'narrow' })}>
                            <i className="ellipsis">${clog.date}</i></span>
                            <span key=${clog.hash} className="alignleft">
                            <i className="ellipsis">${clog.hash}</i></span>`
                    })}
                </div>
            </div>
    </div>`;
  }

//export default Changelogs